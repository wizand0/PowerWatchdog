package ru.wizand.powerwatchdog.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import ru.wizand.powerwatchdog.databinding.FragmentLogBinding

class LogFragment : Fragment() {

    private var _vb: FragmentLogBinding? = null
    private val vb get() = _vb!!
    private val vm: LogViewModel by viewModels()
    private lateinit var adapter: LogAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _vb = FragmentLogBinding.inflate(inflater, container, false)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = LogAdapter()
        vb.recycler.layoutManager = LinearLayoutManager(requireContext())
        vb.recycler.adapter = adapter

        vm.events.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            vb.recycler.scrollToPosition(0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _vb = null
    }
}
